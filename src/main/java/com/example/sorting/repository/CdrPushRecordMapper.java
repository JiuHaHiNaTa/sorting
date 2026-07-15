package com.example.sorting.repository;

import com.example.sorting.entity.CdrPushRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CdrPushRecordMapper {
    int batchInsert(@Param("records") List<CdrPushRecord> records);
    List<CdrPushRecord> selectPendingByLimit(@Param("limit") int limit);
    int updateStatus(@Param("id") String id, @Param("pushStatus") String pushStatus,
                     @Param("pushedAt") Object pushedAt, @Param("failReason") String failReason);
    List<CdrPushRecord> selectByCdrRecordId(@Param("cdrRecordId") String cdrRecordId);
}