package com.example.sorting.repository;

import com.example.sorting.entity.SortingTask;
import com.example.sorting.entity.SortingTaskBackup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分拣任务 Mapper
 */
@Mapper
public interface SortingTaskMapper {

    int insert(SortingTask task);

    int updateStatus(@Param("id") String id, @Param("status") String status);

    int updateStep(@Param("id") String id, @Param("currentStep") String step);

    int incrementRetry(@Param("id") String id);

    int updateErrorMessage(@Param("id") String id, @Param("errorMessage") String msg);

    SortingTask selectById(@Param("id") String id);

    List<SortingTask> selectByStatus(@Param("status") String status);

    List<SortingTask> selectRunningTimeoutTasks(@Param("now") LocalDateTime now);

    List<SortingTask> selectAll();

    int deleteById(@Param("id") String id);

    int insertBackup(SortingTaskBackup backup);
}
