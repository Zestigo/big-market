package com.c.infrastructure.dao;

import com.c.infrastructure.po.UserAwardRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IUserAwardRecordDao {
    void insert(UserAwardRecord userAwardRecord);
}
