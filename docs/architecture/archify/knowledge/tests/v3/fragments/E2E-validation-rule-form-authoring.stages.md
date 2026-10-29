Prueba -> etapa (pw:e2e/real/validation/rule-form-authoring.spec.ts)
- validation_author_form.idc: lineas 59 (nombre, gravedad, ambito), 63 (grupo) y 86-90 (reabierta tras recargar con los mismos valores).
- validation_author_form.tg: lineas 59-60 (mensaje y momentos guardados) y 91 (momento marcado tras recargar).
- validation_author_form.ex (direct): tercer test, lineas 208 (antes de guardar el servidor no tiene la regla), 217 (los 13 tipos de nodo guardados en la expresion que lee la API), 218-224 (estructura exacta: condicional, existe, para-todo, navegacion de relacion, variable, consulta, funcion) y 228 (mismo conjunto de 13 tipos en el fichero del backend). Primer test, lineas 69-74 (condicion con 5 tipos) y 106 (los 13 tipos se ofrecen).
- Fuera de alcance: ejecutar la regla contra un modelo y otras funciones u operadores.
