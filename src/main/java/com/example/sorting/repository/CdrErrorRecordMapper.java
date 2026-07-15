package com.example.sorting.repository;

import com.example.sorting.entity.CdrErrorRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CdrErrorRecordMapper {
    int insert(CdrErrorRecord record);
    List<CdrErrorRecord> selectByTaskId(@Param("taskId") String taskId);
    List<CdrErrorRecord> selectOldRecords(@Param("before") LocalDateTime before);
    int deleteOldRecords(@Param("before") LocalDateTime before);
}