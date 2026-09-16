package com.storehub.dto;

import com.storehub.entity.Employee;
import com.storehub.entity.EmployeeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class EmployeeResponse {
    private Long id;
    private String employeeCode;
    private String name;
    private String mobile;
    private String email;
    private String designation;
    private String department;
    private String address;
    private Long cityId;
    private String cityName;
    private Long stateId;
    private String stateName;
    private LocalDate joiningDate;
    private EmployeeStatus status;
    private String notes;
    private Long linkedUserId;
    private String linkedUserEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EmployeeResponse fromEntity(Employee e) {
        return fromEntity(e, null);
    }

    /** Overload that also carries the Employee -> User link (spec section 7), resolved by the caller via a reverse lookup — Employee itself holds no FK to User. */
    public static EmployeeResponse fromEntity(Employee e, com.storehub.entity.User linkedUser) {
        return EmployeeResponse.builder()
                .id(e.getId())
                .employeeCode(e.getEmployeeCode())
                .name(e.getName())
                .mobile(e.getMobile())
                .email(e.getEmail())
                .designation(e.getDesignation())
                .department(e.getDepartment())
                .address(e.getAddress())
                .cityId(e.getCity() != null ? e.getCity().getId() : null)
                .cityName(e.getCity() != null ? e.getCity().getName() : null)
                .stateId(e.getState() != null ? e.getState().getId() : null)
                .stateName(e.getState() != null ? e.getState().getName() : null)
                .joiningDate(e.getJoiningDate())
                .status(e.getStatus())
                .notes(e.getNotes())
                .linkedUserId(linkedUser != null ? linkedUser.getId() : null)
                .linkedUserEmail(linkedUser != null ? linkedUser.getEmail() : null)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
