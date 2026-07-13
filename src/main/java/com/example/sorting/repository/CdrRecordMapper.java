package com.example.sorting.repository;

import com.example.sorting.entity.CdrRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CDR 话单记录 Mapper
 */
@Mapper
public interface CdrRecordMapper {

    int batchInsert(@Param("records") List<CdrRecord> records);

    int countByOperatorId(@Param("operatorId") String operatorId);

    int countByAzId(@Param("azId") String azId);

    int countByUnitId(@Param("unitId") String unitId);
}
