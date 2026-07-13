package com.example.sorting.repository;

import com.example.sorting.entity.UsageUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用量单位 Mapper
 */
@Mapper
public interface UsageUnitMapper {

    int insert(UsageUnit unit);

    int updateById(UsageUnit unit);

    int deleteById(@Param("id") String id);

    UsageUnit selectById(@Param("id") String id);

    UsageUnit selectByCode(@Param("code") String code);

    List<UsageUnit> selectAll();

    int countByCode(@Param("code") String code);
}
