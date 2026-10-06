package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var tokenPattern = regexp.MustCompile(`\{\{\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239})\s*}}`)

type Request struct {
	SchemaVersion    string `json:"schemaVersion"`
	TransformationID string `json:"transformationId"`
	Kind             string `json:"kind"`
	Direction        string `json:"direction"`
	SourceModelFile  string `json:"sourceModelFile"`
	OutputDirectory  string `json:"outputDirectory"`
	ResultFile       string `json:"resultFile"`
}

type SourceElement struct {
	ID         string         `json:"id"`
	ClassID    string         `json:"classId"`
	Attributes map[string]any `json:"attributes"`
}
type SourceModel struct {
	ID             string          `json:"id"`
	Name           string          `json:"name"`
	RootElementIDs []string        `json:"rootElementIds"`
	Elements       []SourceElement `json:"elements"`
}
type Binding struct {
	Kind            string `json:"kind"`
	SourceFeatureID string `json:"sourceFeatureId"`
	TargetFeatureID string `json:"targetFeatureId"`
	LiteralValue    any    `json:"literalValue"`
}
type Rule struct {
	RuleID        string    `json:"ruleId"`
	SourceTypeIDs []string  `json:"sourceTypeIds"`
	TargetTypeID  string    `json:"targetTypeId"`
	Bindings      []Binding `json:"bindings"`
}
type GenerationRule struct {
	RuleID             string `json:"ruleId"`
	ContextTypeID      string `json:"contextTypeId"`
	OutputPathTemplate string `json:"outputPathTemplate"`
	ContentTemplate    string `json:"contentTemplate"`
	OverwritePolicy    string `json:"overwritePolicy"`
}
type Plan struct {
	Kind                       string           `json:"kind"`
	TransformationID           string           `json:"transformationId"`
	TransformationName         string           `json:"transformationName"`
	TargetMetamodelID          string           `json:"targetMetamodelId"`
	TargetMetamodelFingerprint string           `json:"targetMetamodelFingerprint"`
	Rules                      []Rule           `json:"rules"`
	GenerationRules            []GenerationRule `json:"generationRules"`
}
type TargetElement struct {
	ID          string            `json:"id"`
	ClassID     string            `json:"classId"`
	Attributes  map[string]any    `json:"attributes"`
	Annotations map[string]string `json:"annotations"`
}
type TargetModel struct {
	SchemaVersion  string            `json:"schemaVersion"`
	ID             string            `json:"id"`
	MetamodelID    string            `json:"metamodelId"`
	MetamodelEtag  string            `json:"metamodelEtag"`
	Name           string            `json:"name"`
	RootElementIDs []string          `json:"rootElementIds"`
	Elements       []TargetElement   `json:"elements"`
	Relations      []any             `json:"relations"`
	Annotations    map[string]string `json:"annotations"`
}
type Artifact struct {
	Path string `json:"path"`
	File string `json:"file"`
}
type BridgeResult struct {
	SchemaVersion    string     `json:"schemaVersion"`
	TransformationID string     `json:"transformationId"`
	Language         string     `json:"language"`
	Kind             string     `json:"kind"`
	Direction        string     `json:"direction"`
	TargetModelFile  string     `json:"targetModelFile"`
	Artifacts        []Artifact `json:"artifacts"`
	Diagnostics      []string   `json:"diagnostics"`
}

func fail(message string, code int) { fmt.Fprintln(os.Stderr, message); os.Exit(code) }
func safe(workspace, relative string) string {
	raw := strings.ReplaceAll(strings.TrimSpace(relative), "\\", "/")
	if raw == "" || strings.HasPrefix(raw, "/") || (len(raw) > 1 && raw[1] == ':') {
		fail("HYDRA Go native runtime received an invalid workspace-relative path", 64)
	}
	target, err := filepath.Abs(filepath.Join(workspace, filepath.FromSlash(raw)))
	if err != nil {
		fail("HYDRA Go native runtime could not resolve workspace path", 64)
	}
	base, _ := filepath.Abs(workspace)
	rel, err := filepath.Rel(base, target)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) {
		fail("HYDRA Go native runtime path escapes the isolated workspace", 64)
	}
	return target
}
func readJSON(path string, target any) {
	data, err := os.ReadFile(path)
	if err != nil {
		fail("HYDRA Go native runtime could not read JSON evidence", 65)
	}
	if err := json.Unmarshal(data, target); err != nil {
		fail("HYDRA Go native runtime received invalid JSON evidence", 65)
	}
}
func writeJSON(path string, value any) {
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		fail("HYDRA Go native runtime could not encode evidence", 70)
	}
	data = append(data, '\n')
	if err := os.WriteFile(path, data, 0600); err != nil {
		fail("HYDRA Go native runtime could not persist evidence", 70)
	}
}
func contains(values []string, expected string) bool {
	for _, v := range values {
		if v == expected {
			return true
		}
	}
	return false
}
func stringify(value any) string {
	if value == nil {
		return ""
	}
	return fmt.Sprint(value)
}
func render(template string, element SourceElement) string {
	return tokenPattern.ReplaceAllStringFunc(template, func(raw string) string {
		parts := tokenPattern.FindStringSubmatch(raw)
		token := parts[1]
		if token == "id" {
			return element.ID
		}
		if token == "classId" {
			return element.ClassID
		}
		return stringify(element.Attributes[strings.TrimPrefix(token, "attr:")])
	})
}
func validateLogicalPath(path string) string {
	normalized := strings.ReplaceAll(strings.TrimSpace(path), "\\", "/")
	if normalized == "" || strings.HasPrefix(normalized, "/") || strings.Contains(normalized, "../") || normalized == ".." || (len(normalized) > 1 && normalized[1] == ':') {
		fail("Generated artifact path must be relative and portable: "+path, 78)
	}
	return normalized
}

func executeM2T(workspace string, request Request, source SourceModel, plan Plan) {
	if request.Direction != "FORWARD" {
		fail("HYDRA Go native M2T runtime supports FORWARD only", 78)
	}
	artifacts := map[string]string{}
	order := []string{}
	for _, element := range source.Elements {
		for _, rule := range plan.GenerationRules {
			if element.ClassID != rule.ContextTypeID {
				continue
			}
			logical := validateLogicalPath(render(rule.OutputPathTemplate, element))
			content := render(rule.ContentTemplate, element)
			if _, exists := artifacts[logical]; exists && (rule.OverwritePolicy == "" || rule.OverwritePolicy == "FAIL") {
				fail("Generated artifact collision: "+logical, 78)
			}
			if _, exists := artifacts[logical]; !exists {
				order = append(order, logical)
			}
			artifacts[logical] = content
		}
	}
	outputDir := safe(workspace, request.OutputDirectory)
	if err := os.MkdirAll(outputDir, 0700); err != nil {
		fail("HYDRA Go native runtime could not create output directory", 70)
	}
	evidence := make([]Artifact, 0, len(order))
	for i, logical := range order {
		file := filepath.Join(outputDir, fmt.Sprintf("artifact-%06d.txt", i+1))
		if err := os.WriteFile(file, []byte(artifacts[logical]), 0600); err != nil {
			fail("HYDRA Go native runtime could not persist generated artifact", 70)
		}
		rel, _ := filepath.Rel(workspace, file)
		evidence = append(evidence, Artifact{logical, filepath.ToSlash(rel)})
	}
	result := BridgeResult{request.SchemaVersion, request.TransformationID, "GO", "M2T", request.Direction, "", evidence, []string{"Executed by HYDRA Official Go Native M2T Runtime"}}
	writeJSON(safe(workspace, request.ResultFile), result)
}

func executeM2M(workspace string, request Request, source SourceModel, plan Plan) {
	roots := map[string]bool{}
	for _, id := range source.RootElementIDs {
		roots[id] = true
	}
	elements := make([]TargetElement, 0)
	targetRoots := make([]string, 0)
	counter := 0
	for _, sourceElement := range source.Elements {
		for _, rule := range plan.Rules {
			if !contains(rule.SourceTypeIDs, sourceElement.ClassID) {
				continue
			}
			counter++
			targetID := fmt.Sprintf("native-%d", counter)
			attrs := map[string]any{}
			for _, binding := range rule.Bindings {
				switch binding.Kind {
				case "DIRECT":
					attrs[binding.TargetFeatureID] = sourceElement.Attributes[binding.SourceFeatureID]
				case "CONSTANT":
					attrs[binding.TargetFeatureID] = binding.LiteralValue
				default:
					fail("HYDRA Go native runtime received an unsupported binding kind", 78)
				}
			}
			elements = append(elements, TargetElement{targetID, rule.TargetTypeID, attrs, map[string]string{}})
			if roots[sourceElement.ID] {
				targetRoots = append(targetRoots, targetID)
			}
		}
	}
	digest := sha256.Sum256([]byte(plan.TransformationID + "|" + request.Direction + "|" + source.ID))
	modelID := "native-model-" + hex.EncodeToString(digest[:])[:32]
	target := TargetModel{"1.0", modelID, plan.TargetMetamodelID, plan.TargetMetamodelFingerprint, source.Name + " native " + plan.TransformationName, targetRoots, elements, []any{}, map[string]string{"hydra.native.runtime": "GO", "hydra.transformation.direction": request.Direction}}
	outputDir := safe(workspace, request.OutputDirectory)
	if err := os.MkdirAll(outputDir, 0700); err != nil {
		fail("HYDRA Go native runtime could not create output directory", 70)
	}
	targetPath := filepath.Join(outputDir, "target-model.json")
	writeJSON(targetPath, target)
	rel, _ := filepath.Rel(workspace, targetPath)
	result := BridgeResult{request.SchemaVersion, request.TransformationID, "GO", "M2M", request.Direction, filepath.ToSlash(rel), []Artifact{}, []string{"Executed by HYDRA Official Go Native M2M Runtime"}}
	writeJSON(safe(workspace, request.ResultFile), result)
}

func main() {
	if len(os.Args) != 2 {
		fail("HYDRA Go native runtime expects one bridge request file", 64)
	}
	requestPath, err := filepath.Abs(os.Args[1])
	if err != nil {
		fail("HYDRA Go native runtime could not resolve request", 64)
	}
	workspace := filepath.Dir(requestPath)
	var request Request
	readJSON(requestPath, &request)
	if request.Direction == "" {
		request.Direction = "FORWARD"
	}
	var source SourceModel
	readJSON(safe(workspace, request.SourceModelFile), &source)
	var plan Plan
	readJSON(filepath.Join(workspace, "export", "native", "native-plan.json"), &plan)
	kind := request.Kind
	if kind == "" {
		kind = plan.Kind
	}
	if kind == "" {
		kind = "M2M"
	}
	if kind == "M2T" {
		executeM2T(workspace, request, source, plan)
	} else if kind == "M2M" {
		executeM2M(workspace, request, source, plan)
	} else {
		fail("HYDRA Go native runtime received an unsupported transformation kind", 78)
	}
}
