Prueba -> etapa (pw:e2e/real/validation/rule-designer-authoring.spec.ts)
- validation_define_rule.id: lineas 29 (codigo normalizado) y 44-47 (regla con nombre, codigo, gravedad y ambito en la API y en disco).
- validation_define_rule.blk: lineas 60-63 (aviso visible, sin respuesta de validaciones ni fichero) y 69-72 (tras corregir se guarda y el aviso desaparece).
- validation_define_rule.meta (direct): tercer test, lineas 85-88 (con el selector de contexto vaciado el disenador se bloquea y el servidor no guarda nada) y 99-103 (clases y atributos ofrecidos = los que devuelve la API para el metamodelo guardado). Primer test, lineas 35-37 (Person y Person.age ofrecidos).
