package com.example.editor.validation.validation;

/** Momentos en los que puede solicitarse una regla. */
public enum ValidationTrigger {
    ON_CREATE, ON_ATTRIBUTE_CHANGE, ON_RELATION_CHANGE, ON_DELETE, ON_OPEN, ON_SAVE, MANUAL, BEFORE_EXPORT, BACKGROUND, FULL_VALIDATION
}
