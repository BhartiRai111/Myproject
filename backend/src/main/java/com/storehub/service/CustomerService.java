package com.storehub.service;

import com.storehub.dto.CustomerCreateRequest;
import com.storehub.dto.CustomerResponse;
import com.storehub.entity.Customer;
import com.storehub.exception.CustomerNotFoundException;
import com.storehub.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll(Sort.by("firstName").ascending()).stream()
                .map(CustomerResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CustomerResponse createCustomer(CustomerCreateRequest request) {
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .build();

        return CustomerResponse.fromEntity(customerRepository.save(customer));
    }

    public Customer findCustomerOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
