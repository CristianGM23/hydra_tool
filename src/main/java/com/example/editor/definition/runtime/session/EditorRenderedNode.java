package com.example.editor.definition.runtime.session;
import java.util.List;
import java.util.Map;
import com.example.editor.definition.EditorImageFit;
import com.example.editor.definition.EditorNodeShape;
public record EditorRenderedNode(String elementId, String classId, String className, String mappingId,
        String representationName, EditorNodeShape shape, String label, double width, double height, double minimumWidth, double minimumHeight,
        Map<String,Object> attributes, Map<String,String> styles, String imageAssetPath, EditorImageFit imageFit,
        boolean container, List<String> containmentRelationIds, double containerPadding, List<EditorNodeAppearanceView> availableAppearances) {
    public EditorRenderedNode {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        styles = styles == null ? Map.of() : Map.copyOf(styles);
        imageAssetPath = imageAssetPath == null ? "" : imageAssetPath;
        if(imageFit == null) imageFit = EditorImageFit.CONTAIN;
        containmentRelationIds = containmentRelationIds == null ? List.of() : List.copyOf(containmentRelationIds);
        availableAppearances = availableAppearances == null ? List.of() : List.copyOf(availableAppearances);
        if(minimumWidth <= 0) minimumWidth = Math.min(width > 0 ? width : 80, 80);
        if(minimumHeight <= 0) minimumHeight = Math.min(height > 0 ? height : 50, 50);
        if(containerPadding <= 0) containerPadding = 18;
    }
}
