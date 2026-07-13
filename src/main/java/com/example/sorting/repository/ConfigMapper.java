package com.example.sorting.repository;

import com.example.sorting.entity.FileServerConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ConfigMapper {

    int insert(FileServerConfig config);

    Optional<FileServerConfig> findById(@Param("id") String id);

    int update(FileServerConfig config);

    int updateConnectivityStatus(@Param("id") String id, @Param("status") Boolean status);

    int updateEnabled(@Param("id") String id, @Param("enabled") Boolean enabled);

    List<FileServerConfig> findAll();
}
