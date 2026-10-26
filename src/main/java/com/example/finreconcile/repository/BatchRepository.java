package com.example.finreconcile.repository;

import com.example.finreconcile.domain.BatchData;

import java.util.List;

public interface BatchRepository {

    List<String> listBatchIds();

    BatchData load(String batchId);
}
