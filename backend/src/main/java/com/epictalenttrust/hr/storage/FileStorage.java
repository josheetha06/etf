package com.epictalenttrust.hr.storage;

/** Private storage for CV files and extracted text. Keys look like "cv/{company}/{job}/{application}.pdf". */
public interface FileStorage {

    void put(String key, byte[] content);

    byte[] get(String key);

    void delete(String key);
}
