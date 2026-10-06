#!/usr/bin/env python3
"""Añade documentación española compacta a métodos Java mantenidos por HYDRA."""
from __future__ import annotations
from pathlib import Path
import re

ROOT = Path('src/main/java')
CONTROL = {'if','for','while','switch','catch','return','throw','new','assert','this','super','synchronized','try','else','do','case'}
MODIFIERS = {'public','protected','private','static','final','abstract','synchronized','native','strictfp','default','sealed','non-sealed'}
TYPE_WORDS = {'class','record','interface','enum','@interface'}


def mask_java(text: str) -> str:
    """Oculta comentarios y literales conservando posiciones y saltos de línea."""
    out=list(text); i=0; n=len(text); state='code'
    while i<n:
        c=text[i]; d=text[i+1] if i+1<n else ''
        if state=='code':
            if c=='/' and d=='/': out[i]=out[i+1]=' '; i+=2; state='line'; continue
            if c=='/' and d=='*': out[i]=out[i+1]=' '; i+=2; state='block'; continue
            if text.startswith('\"\"\"', i):
                out[i]=out[i+1]=out[i+2]=' '; i+=3; state='textblock'; continue
            if c=='"': out[i]=' '; i+=1; state='string'; continue
            if c=="'": out[i]=' '; i+=1; state='char'; continue
        elif state=='line':
            if c=='\n': state='code'
            else: out[i]=' '
            i+=1; continue
        elif state=='block':
            if c=='*' and d=='/': out[i]=out[i+1]=' '; i+=2; state='code'; continue
            if c!='\n': out[i]=' '
            i+=1; continue
        elif state=='textblock':
            if text.startswith('\"\"\"', i):
                out[i]=out[i+1]=out[i+2]=' '; i+=3; state='code'; continue
            if c!='\n': out[i]=' '
            i+=1; continue
        elif state in {'string','char'}:
            quote='"' if state=='string' else "'"
            if c=='\\':
                if c!='\n': out[i]=' '
                if i+1<n and text[i+1]!='\n': out[i+1]=' '
                i+=2; continue
            if c==quote: out[i]=' '; i+=1; state='code'; continue
            if c!='\n': out[i]=' '
            i+=1; continue
        i+=1
    return ''.join(out)


def class_names(masked: str) -> set[str]:
    return set(re.findall(r'\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)', masked))


def matching_parentheses(masked: str) -> dict[int, int]:
    """Calcula una sola vez las parejas de paréntesis del archivo."""
    stack=[]; pairs={}
    for i,c in enumerate(masked):
        if c=='(': stack.append(i)
        elif c==')' and stack:
            opening=stack.pop(); pairs[opening]=i
    return pairs

def split_params(raw: str) -> list[str]:
    parts=[]; start=0; angle=paren=bracket=0
    for i,c in enumerate(raw):
        if c=='<': angle+=1
        elif c=='>': angle=max(0,angle-1)
        elif c=='(': paren+=1
        elif c==')': paren=max(0,paren-1)
        elif c=='[': bracket+=1
        elif c==']': bracket=max(0,bracket-1)
        elif c==',' and angle==paren==bracket==0:
            parts.append(raw[start:i]); start=i+1
    parts.append(raw[start:])
    return [p.strip() for p in parts if p.strip()]


def param_names(raw: str) -> list[str]:
    names=[]
    for part in split_params(raw):
        clean=re.sub(r'@[\w$.]+(?:\([^)]*\))?\s*','',part)
        clean=re.sub(r'\bfinal\s+','',clean).replace('...',' ')
        ids=re.findall(r'[A-Za-z_$][\w$]*',clean)
        if ids: names.append(ids[-1])
    return names


def describe(name: str, owner: str, constructor: bool) -> str:
    if constructor: return f'Inicializa una instancia de {owner} con sus dependencias y datos de entrada.'
    low=name.lower()
    if low.startswith(('get','load','read','find','list','search','resolve','lookup','fetch')): return f'Obtiene la información correspondiente a «{name}» dentro de {owner}.'
    if low.startswith(('is','has','can','should','matches','contains','exists','supports')): return f'Evalúa la condición «{name}» sin modificar el estado externo.'
    if low.startswith(('save','write','update','set','apply','replace','merge','synchronize','sync')): return f'Actualiza el estado gestionado por {owner} mediante la operación «{name}».'
    if low.startswith(('delete','remove','clear','close','discard','invalidate')): return f'Elimina o libera el recurso gestionado por «{name}» de forma controlada.'
    if low.startswith(('create','build','generate','new','open','start','initialize','init')): return f'Crea o prepara el resultado de «{name}» dentro de {owner}.'
    if low.startswith(('validate','check','verify','require','ensure')): return f'Valida las condiciones necesarias para «{name}» y rechaza entradas no válidas.'
    if low.startswith(('handle','process','execute','run','perform','dispatch','publish','emit')): return f'Procesa la operación «{name}» coordinando las responsabilidades de {owner}.'
    return f'Ejecuta la operación «{name}» correspondiente a la responsabilidad de {owner}.'


def input_text(names: list[str]) -> str:
    if not names: return 'no requiere parámetros de entrada.'
    def one(name: str) -> str:
        low=name.lower()
        if low.endswith('id') or low in {'id','key'}: what='identificador que delimita el recurso objetivo'
        elif 'path' in low or low in {'root','directory'}: what='ruta o ubicación utilizada por la operación'
        elif 'request' in low: what='solicitud con los datos necesarios para ejecutar la operación'
        elif 'file' in low or 'archive' in low: what='archivo o contenido que debe procesarse'
        elif low in {'value','input','text','content','payload'}: what='valor de entrada que debe procesarse'
        elif low in {'items','values','entries','paths','files','list'}: what='colección de elementos que debe procesarse'
        elif 'service' in low: what='servicio colaborador utilizado por la operación'
        elif 'properties' in low or 'config' in low or 'options' in low: what='configuración que gobierna el comportamiento'
        elif 'mapper' in low or 'writer' in low or 'parser' in low: what='componente de conversión utilizado durante el procesamiento'
        else: what='dato de entrada necesario para la operación'
        return f'{name}: {what}'
    return '; '.join(one(n) for n in names)+'.'


def output_text(prefix: str, name: str, constructor: bool) -> str:
    if constructor: return 'deja la instancia inicializada y lista para su uso.'
    if re.search(r'\bvoid\b',prefix): return 'no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente.'
    if re.search(r'\bboolean\b',prefix) or name.lower().startswith(('is','has','can','should','matches','contains','exists','supports')): return 'devuelve un indicador que expresa el resultado de la condición.'
    return 'devuelve el resultado definido por el contrato del método.'


def declaration_candidates(text: str) -> list[tuple[int,int,str,list[str],str,bool,str]]:
    masked=mask_java(text); classes=class_names(masked); pairs=matching_parentheses(masked); result=[]
    for m in re.finditer(r'\(', masked):
        op=m.start(); close=pairs.get(op)
        if close is None: continue
        # Inspect only the current physical line. HYDRA's maintained Java sources place
        # the method name and its declaration prefix on that line even when parameters continue.
        line_start=masked.rfind('\n', max(0, op-2000), op)+1
        before=masked[line_start:op]
        nm=re.search(r'([A-Za-z_$][\w$]*)\s*$', before)
        if not nm: continue
        name=nm.group(1)
        if name in CONTROL: continue
        name_start=line_start+nm.start(1)
        segment=masked[line_start:name_start]
        local_boundary=max(segment.rfind('}'),segment.rfind(';'))
        decl_start=line_start+(local_boundary+1 if local_boundary>=0 else 0)
        while decl_start<name_start and masked[decl_start].isspace(): decl_start+=1
        prefix=masked[decl_start:name_start].strip()
        if not prefix and name not in classes: continue
        if any(re.search(r'\b'+re.escape(word)+r'\b',prefix) for word in TYPE_WORDS): continue
        if any(re.search(r'\b'+word+r'\b',prefix) for word in CONTROL): continue
        if '=' in prefix or '->' in prefix: continue
        if '(' in prefix or ')' in prefix: continue
        if re.search(r'[A-Za-z_$][\w$]*\s*\.\s*$',prefix): continue
        if prefix.endswith('@'): continue
        tokens=re.findall(r'[A-Za-z_$][\w$-]*',prefix)
        constructor=name in classes and all(t in MODIFIERS for t in tokens)
        if not constructor:
            meaningful=[t for t in tokens if t not in MODIFIERS]
            if not meaningful or meaningful[-1] in TYPE_WORDS: continue
        tail=masked[close+1: close+500]
        if not re.match(r'\s*(?:throws\b[^\{;]*\s*)?(?:default\b[^;]*;|\{|;)',tail,re.S): continue
        params=param_names(text[op+1:close])
        owner=name if constructor else (next(iter(classes),'tipo'))
        result.append((decl_start,name_start,name,params,prefix,constructor,owner))
    unique={x[1]:x for x in result}
    return [unique[k] for k in sorted(unique)]

def documentation_comment(name: str, params: list[str], prefix: str, constructor: bool, owner: str) -> str:
    return '/** Descripción: '+describe(name,owner,constructor)+' Entradas: '+input_text(params)+' Salida: '+output_text(prefix,name,constructor)+' */ '


def document_file(path: Path) -> tuple[int,int]:
    text=path.read_text(encoding='utf-8')
    candidates=declaration_candidates(text)
    insertions=[]; already=0
    for start, name_start, name, params, prefix, constructor, owner in candidates:
        line_start=text.rfind('\n',0,name_start)+1
        nearby=text[line_start:name_start]
        if 'Descripción:' in nearby: already+=1; continue
        insertions.append((start,documentation_comment(name,params,prefix,constructor,owner)))
    for pos,comment in reversed(insertions): text=text[:pos]+comment+text[pos:]
    if insertions: path.write_text(text,encoding='utf-8')
    return len(insertions),already


def main() -> None:
    added=existing=files=0
    for path in sorted(ROOT.rglob('*.java')):
        a,e=document_file(path); added+=a; existing+=e
        if a: files+=1
    print(f'BACKEND_SPANISH_METHOD_DOCS added={added} already={existing} files={files}')

if __name__=='__main__': main()
