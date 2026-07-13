package com.example.sorting.repository;

import com.example.sorting.entity.Operator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 运营商 Mapper
 */
@Mapper
public interface OperatorMapper {

    int insert(Operator operator);

    int updateById(Operator operator);

    int deleteById(@Param("id") String id);

    Operator selectById(@Param("id") String id);

    Operator selectByCode(@Param("code") String code);

    List<Operator> selectAll();

    int countByCode(@Param("code") String code);
}
