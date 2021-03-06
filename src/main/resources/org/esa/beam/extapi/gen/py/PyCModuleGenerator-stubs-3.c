
PyObject* beam_new_pyseq_from_${typeName}_array(const ${ctype}* elems, int length)
{
    PyObject* list;
    PyObject* item;
    int i;
    list = PyList_New(length);
    if (list == NULL) {
        return NULL;
    }
    for (i = 0; i < length; i++) {
        item = ${elemToItemCall};
        if (item == NULL) {
            Py_DECREF(list);
            return NULL;
        }
        if (PyList_SetItem(list, i, item) != 0) {
            Py_DECREF(item);
            Py_DECREF(list);
            return NULL;
        }
    }
    return list;
}

${ctype}* beam_new_${typeName}_array_from_pyseq(PyObject* seq, int* length)
{
    Py_ssize_t size;
    ${ctype}* elems;
    Py_ssize_t i;
    PyObject* item;

    size = PySequence_Size(seq);
    elems = (${ctype}*) malloc(size * sizeof (${ctype}));
    if (elems == NULL) {
        /* todo: throw Python exception */
        return NULL;
    }
    for (i = 0; i < size; i++) {
        item = PySequence_GetItem(seq, i);
        if (item == NULL) {
            free(elems);
            return NULL;
        }
        elems[i] = ${itemToElemCall};
    }
    /* todo: check if conversion to int is ok */
    *length = (int) size;
    return elems;
}
