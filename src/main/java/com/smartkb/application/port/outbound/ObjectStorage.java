package com.smartkb.application.port.outbound;

import java.io.InputStream;

/**
 * 原始文档对象存储端口。
 */
public interface ObjectStorage {

    void put(String objectKey, InputStream content, long size, String contentType);

    InputStream get(String objectKey);

    void delete(String objectKey);
}
