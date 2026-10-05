package com.querybuilder;

import java.util.List;
public record PagedResult<T>(List<T> pageResult, int page, int pageSize, long count, int totalPages){}

