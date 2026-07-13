package com.example.sorting.repository;

import com.example.sorting.entity.ServiceAz;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 服务可用区 Mapper
 */
@Mapper
public interface ServiceAzMapper {

    int insert(ServiceAz az);

    int updateById(ServiceAz az);

    int deleteById(@Param("id") String id);

    ServiceAz selectById(@Param("id") String id);

    ServiceAz selectByCode(@Param("code") String code);

    List<ServiceAz> selectAll();

    int countByCode(@Param("code") String code);
}
