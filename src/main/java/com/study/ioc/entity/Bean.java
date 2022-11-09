package com.study.ioc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class Bean {
    private String id;
    private Object value;
}
