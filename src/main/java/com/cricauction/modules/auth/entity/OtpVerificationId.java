package com.cricauction.modules.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationId implements Serializable {
    private String mobile;
    private String purpose;
}
