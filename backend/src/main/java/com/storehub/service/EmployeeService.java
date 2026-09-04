package com.storehub.service;

import com.storehub.dto.EmployeeRequest;
import com.storehub.dto.EmployeeResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.City;
import com.storehub.entity.Employee;
import com.storehub.entity.EmployeeStatus;
import com.storehub.entity.State;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CityService cityService;
    private final StateService stateService;

    public PagedResponse<EmployeeResponse> search(String search, EmployeeStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Employee> result = employeeRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(EmployeeResponse::fromEntity));
    }

    public EmployeeResponse getById(Long id) {
        return EmployeeResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(request.getEmployeeCode())) {
            throw new BadRequestException("An employee with code '" + request.getEmployeeCode() + "' already exists");
        }
        if (employeeRepository.existsByMobile(request.getMobile())) {
            throw new BadRequestException("An employee with mobile number '" + request.getMobile() + "' already exists");
        }
        if (StringUtils.hasText(request.getEmail()) && employeeRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("An employee with email '" + request.getEmail() + "' already exists");
        }

        City city = request.getCityId() != null ? cityService.findOrThrow(request.getCityId()) : null;
        State state = request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null;

        Employee employee = Employee.builder()
                .employeeCode(request.getEmployeeCode())
                .name(request.getName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .designation(request.getDesignation())
                .department(request.getDepartment())
                .address(request.getAddress())
                .city(city)
                .state(state)
                .joiningDate(request.getJoiningDate())
                .notes(request.getNotes())
                .build();

        return EmployeeResponse.fromEntity(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = findOrThrow(id);

        if (!employee.getEmployeeCode().equalsIgnoreCase(request.getEmployeeCode())
                && employeeRepository.existsByEmployeeCodeIgnoreCaseAndIdNot(request.getEmployeeCode(), id)) {
            throw new BadRequestException("An employee with code '" + request.getEmployeeCode() + "' already exists");
        }
        if (!employee.getMobile().equals(request.getMobile())
                && employeeRepository.existsByMobileAndIdNot(request.getMobile(), id)) {
            throw new BadRequestException("An employee with mobile number '" + request.getMobile() + "' already exists");
        }
        if (StringUtils.hasText(request.getEmail())
                && !request.getEmail().equalsIgnoreCase(employee.getEmail())
                && employeeRepository.existsByEmailIgnoreCaseAndIdNot(request.getEmail(), id)) {
            throw new BadRequestException("An employee with email '" + request.getEmail() + "' already exists");
        }

        City city = request.getCityId() != null ? cityService.findOrThrow(request.getCityId()) : null;
        State state = request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null;

        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setName(request.getName());
        employee.setMobile(request.getMobile());
        employee.setEmail(request.getEmail());
        employee.setDesignation(request.getDesignation());
        employee.setDepartment(request.getDepartment());
        employee.setAddress(request.getAddress());
        employee.setCity(city);
        employee.setState(state);
        employee.setJoiningDate(request.getJoiningDate());
        employee.setNotes(request.getNotes());

        return EmployeeResponse.fromEntity(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse setStatus(Long id, EmployeeStatus status) {
        Employee employee = findOrThrow(id);
        employee.setStatus(status);
        return EmployeeResponse.fromEntity(employeeRepository.save(employee));
    }

    public Employee findOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Employee", id));
    }
}
