package com.darshan.circl.participation.dto;

import java.util.List;

public record MyActivitiesPage(List<MyActivity> items, String nextCursor) {
}
