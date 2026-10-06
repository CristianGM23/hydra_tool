import { HydraButton } from '../design-system/HydraButton';
import { HydraPalette } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { MetamodelConnectionKind } from './types';

interface MetamodelPaletteProps {
    connectionKind: MetamodelConnectionKind | null;
    connectionSourceClassId: string;
    onAddClass: (kind: 'CLASS' | 'ABSTRACT' | 'INTERFACE') => void;
    onAddEnum: () => void;
    onAddDataType: () => void;
    onConnectionKindChange: (kind: MetamodelConnectionKind | null) => void;
}

/**
 * Muestra los elementos y relaciones disponibles para crear el metamodelo.
 *
 * @param props contiene las operaciones de creación.
 * @returns paleta lateral del diseñador.
 */
export function MetamodelPalette(props: MetamodelPaletteProps) {
    const { t } = useHydraLanguage();
    return (
        <HydraPalette className="metamodel-palette" title={t("Palette")} description={t("Create metamodel elements and relationships.")}>
            <section className="metamodel-palette-section">
                <h3>{t("Elements")}</h3>
                <HydraButton className="metamodel-palette-button" type="button" onClick={() => props.onAddClass('CLASS')}>
                    <ElementIcon kind="CLASS" />
                    <span className="metamodel-palette-button-text"><strong>{t("Class")}</strong><small>{t("Concrete class")}</small></span>
                </HydraButton>
                <HydraButton className="metamodel-palette-button" type="button" onClick={() => props.onAddClass('ABSTRACT')}>
                    <ElementIcon kind="ABSTRACT" />
                    <span className="metamodel-palette-button-text"><strong>{t("Abstract class")}</strong><small>{t("Non-instantiable class")}</small></span>
                </HydraButton>
                <HydraButton className="metamodel-palette-button" type="button" onClick={() => props.onAddClass('INTERFACE')}>
                    <ElementIcon kind="INTERFACE" />
                    <span className="metamodel-palette-button-text"><strong>{t("Interface")}</strong><small>{t("Interface contract")}</small></span>
                </HydraButton>
                <HydraButton className="metamodel-palette-button" type="button" onClick={props.onAddEnum}>
                    <ElementIcon kind="ENUM" />
                    <span className="metamodel-palette-button-text"><strong>{t("Enumeration")}</strong><small>{t("Named set of allowed values")}</small></span>
                </HydraButton>
                <HydraButton className="metamodel-palette-button" type="button" onClick={props.onAddDataType}>
                    <ElementIcon kind="DATA_TYPE" />
                    <span className="metamodel-palette-button-text"><strong>{t("Data type")}</strong><small>{t("Reusable custom value type")}</small></span>
                </HydraButton>
            </section>

            <section className="metamodel-palette-section">
                <h3>{t("Relationships")}</h3>
                <RelationshipButton currentKind={props.connectionKind} kind="REFERENCE" title={t("Reference")} description={t("Directed relationship")}  onSelect={kind => selectRelationship(props, kind)} />
                <RelationshipButton currentKind={props.connectionKind} kind="AGGREGATION" title={t("Aggregation")} description={t("Shared whole-part relationship")}  onSelect={kind => selectRelationship(props, kind)} />
                <RelationshipButton currentKind={props.connectionKind} kind="CONTAINMENT" title={t("Containment")} description={t("Owned whole-part relationship")}  onSelect={kind => selectRelationship(props, kind)} />
                <RelationshipButton currentKind={props.connectionKind} kind="DEPENDENCY" title={t("Dependency")} description={t("Non-owning dependency")}  onSelect={kind => selectRelationship(props, kind)} />
                <RelationshipButton currentKind={props.connectionKind} kind="INHERITANCE" title={t("Inheritance")} description={t("Generalization to a supertype")}  onSelect={kind => selectRelationship(props, kind)} />
                <RelationshipButton currentKind={props.connectionKind} kind="IMPLEMENTATION" title={t("Implementation")} description={t("Class conforms to an interface")}  onSelect={kind => selectRelationship(props, kind)} />
            </section>

            <div className="metamodel-palette-help">
                <p>{t(relationshipHelp(props.connectionKind, props.connectionSourceClassId))}</p>
                <p>{t("Select a blue source point and then the exact green target point, or drag between them.")}</p>
                <p>{t("The same target point accepts multiple incoming connections.")}</p>
            </div>
        </HydraPalette>
    );
}

interface RelationshipButtonProps {
    currentKind: MetamodelConnectionKind | null;
    kind: MetamodelConnectionKind;
    title: string;
    description: string;
    onSelect: (kind: MetamodelConnectionKind) => void;
}

/**
 * Muestra una relación seleccionable de la paleta.
 *
 * @param props contiene la relación.
 * @returns botón de relación.
 */
function RelationshipButton(props: RelationshipButtonProps) {
    return (
        <HydraButton className={relationshipButtonClass(props.currentKind, props.kind)} type="button" onClick={() => props.onSelect(props.kind)}>
            <RelationshipIcon kind={props.kind} />
            <span className="metamodel-palette-button-text"><strong>{props.title}</strong><small>{props.description}</small></span>
        </HydraButton>
    );
}

/**
 * Selecciona o desactiva una herramienta de relación.
 *
 * @param props contiene el estado de la paleta.
 * @param kind identifica la relación.
 * @returns no devuelve ningún valor.
 */
function selectRelationship(props: MetamodelPaletteProps, kind: MetamodelConnectionKind): void {
    if (props.connectionKind === kind) {
        props.onConnectionKindChange(null);
    } else {
        props.onConnectionKindChange(kind);
    }
}

/**
 * Devuelve la clase visual de una relación de la paleta.
 *
 * @param currentKind contiene la relación activa.
 * @param expectedKind contiene la relación evaluada.
 * @returns clase CSS del botón.
 */
function relationshipButtonClass(currentKind: MetamodelConnectionKind | null, expectedKind: MetamodelConnectionKind): string {
    if (currentKind === expectedKind) {
        return 'metamodel-palette-button metamodel-relationship-button selected';
    }

    return 'metamodel-palette-button metamodel-relationship-button';
}

/**
 * Devuelve la ayuda correspondiente al paso actual.
 *
 * @param connectionKind contiene la relación activa.
 * @param sourceClassId contiene la clase de origen.
 * @returns texto de ayuda.
 */
function relationshipHelp(connectionKind: MetamodelConnectionKind | null, sourceClassId: string): string {
    if (connectionKind === null) {
        return 'Select a relationship to start connecting classes.';
    }

    if (sourceClassId === '') {
        return 'Relationship selected. Choose a blue source point.';
    }

    return 'Source point selected. Choose the exact green target point.';
}

/**
 * Dibuja el icono de un elemento del metamodelo.
 *
 * @param kind identifica el elemento.
 * @returns icono SVG.
 */
function ElementIcon({ kind }: { kind: 'CLASS' | 'ABSTRACT' | 'INTERFACE' | 'ENUM' | 'DATA_TYPE' }) {
    let stereotype = 'C';

    if (kind === 'ABSTRACT') {
        stereotype = 'A';
    } else if (kind === 'INTERFACE') {
        stereotype = 'I';
    } else if (kind === 'ENUM') {
        stereotype = 'E';
    } else if (kind === 'DATA_TYPE') {
        stereotype = 'D';
    }

    return (
        <span className="metamodel-palette-icon" aria-hidden="true">
            <svg viewBox="0 0 32 24"><rect x="2" y="2" width="28" height="20" rx="2" /><line x1="2" y1="9" x2="30" y2="9" /><text x="16" y="7" textAnchor="middle">{stereotype}</text></svg>
        </span>
    );
}

/**
 * Dibuja el icono neutral de una relación.
 *
 * @param kind identifica la relación.
 * @returns icono SVG.
 */
function RelationshipIcon({ kind }: { kind: MetamodelConnectionKind }) {
    if (kind === 'CONTAINMENT') {
        return <span className="metamodel-palette-icon" aria-hidden="true"><svg viewBox="0 0 36 24"><polygon className="filled" points="3,12 9,6 15,12 9,18" /><line x1="15" y1="12" x2="31" y2="12" /><polyline points="27,8 31,12 27,16" /></svg></span>;
    }

    if (kind === 'AGGREGATION') {
        return <span className="metamodel-palette-icon" aria-hidden="true"><svg viewBox="0 0 36 24"><polygon points="3,12 9,6 15,12 9,18" /><line x1="15" y1="12" x2="31" y2="12" /><polyline points="27,8 31,12 27,16" /></svg></span>;
    }

    if (kind === 'DEPENDENCY') {
        return <span className="metamodel-palette-icon" aria-hidden="true"><svg viewBox="0 0 36 24"><line className="dashed" x1="3" y1="12" x2="31" y2="12" /><polyline points="27,8 31,12 27,16" /></svg></span>;
    }

    if (kind === 'INHERITANCE') {
        return <span className="metamodel-palette-icon" aria-hidden="true"><svg viewBox="0 0 36 24"><line x1="4" y1="12" x2="25" y2="12" /><polygon points="25,6 33,12 25,18" /></svg></span>;
    }

    if (kind === 'IMPLEMENTATION') {
        return <span className="metamodel-palette-icon" aria-hidden="true"><svg viewBox="0 0 36 24"><line className="dashed" x1="4" y1="12" x2="25" y2="12" /><polygon points="25,6 33,12 25,18" /></svg></span>;
    }

    return <span className="metamodel-palette-icon" aria-hidden="true"><svg viewBox="0 0 36 24"><line x1="3" y1="12" x2="31" y2="12" /><polyline points="27,8 31,12 27,16" /></svg></span>;
}
