package com.sky.dto;

import lombok.Data;

@Data
public class EmployeePasswordDTO {
    private String oldPassword;
    private String newPassword;
}
