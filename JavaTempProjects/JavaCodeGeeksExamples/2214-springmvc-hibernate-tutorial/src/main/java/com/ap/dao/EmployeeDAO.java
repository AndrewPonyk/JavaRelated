package com.ap.dao;

import com.ap.model.Employee;

import java.util.List;

public interface EmployeeDAO {
    Employee findById(int id);

    void saveEmployee(Employee employee);

    void deleteEmployeeBySsn(String ssn);

    List<Employee> findAllEmployees();

    Employee findEmployeeBySsn(String ssn);
}
