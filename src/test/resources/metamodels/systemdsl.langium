grammar SystemDSL

entry SystemModel:
    'system' name=ID
    (
        types+=CustomDataType |
        components+=Component
    )*;

// ==========================================
// 1. ENUMERADOS Y DATA TYPES PERSONALIZADOS
// ==========================================

// El Enum se define así. Langium lo registra en el metamodelo directamente.
enum Environment {
    DEVELOPMENT = 'DEV',
    TESTING     = 'TEST',
    PRODUCTION  = 'PROD'
}

// Data Type 1: IP Address
IPAddress returns string:
    INT '.' INT '.' INT '.' INT;

// Data Type 2: Port
Port returns number:
    INT;

// Custom DataType para entidades del AST
CustomDataType:
    'datatype' name=ID 'extends' baseType=('String' | 'Integer' | 'Boolean');

// ==========================================
// 2. ELEMENTOS DEL SISTEMA Y REFERENCIAS
// ==========================================

type Component = Database | Service;

// Base de Datos (Usa el Enum Environment directamente como tipo de la propiedad 'env')
Database:
    'database' name=ID '{'
        'ip' ip=IPAddress
        'port' port=Port
        ('env' env=Environment)?
    '}';

// Microservicio
Service:
    'service' name=ID '{'
        'port' port=Port
        ('env' env=Environment)?

        // Referencia cruzada a una Database
        ('connectsTo' database=[Database:ID])?

        ('attributes' '{' attributes+=Attribute+ '}')?
    '}';

// Atributo interno
Attribute:
    name=ID ':' (
        type=[CustomDataType:ID] | primitiveType=('string' | 'int' | 'bool')
    );

// ==========================================
// 3. TERMINALES
// ==========================================

terminal ID: /[_a-zA-Z][\w]*/;
terminal INT returns number: /[0-9]+/;
terminal STRING: /"[^"]*"|'[^']*'/;

hidden terminal WS: /\s+/;
hidden terminal ML_COMMENT: /\/\*[\s\S]*?\*\//;
hidden terminal SL_COMMENT: /\/\/[^\n\r]*/;
