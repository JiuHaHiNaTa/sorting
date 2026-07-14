package com.example.sorting.repository;

import com.example.sorting.entity.SortingStepLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分拣步骤日志 Mapper
 */
@Mapper
public interface SortingStepLogMapper {

    int insert(SortingStepLog log);

    List<SortingStepLog> selectByTaskId(@Param("taskId") String taskId);
}
