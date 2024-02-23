package org.mesutormanli.customerapi.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.mesutormanli.customerapi.model.dto.CustomerDto;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerListResponse implements Serializable {
    private List<CustomerDto> customers;

    //add method fo filter customers by name
    public List<CustomerDto> filterByName(String name) {
        return customers.stream().filter(customer -> customer.getName().contains(name)).collect(Collectors.toList());
    }
}
