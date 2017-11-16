package com.ap;

import com.ap.entity.CourseEntity;
import com.ap.model.Course;
import com.ap.model.Student;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//https://stackoverflow.com/questions/29297468/copy-properties-from-one-bean-to-another-not-the-same-class-recursively-inclu

//https://stackoverflow.com/questions/1432764/any-tool-for-java-object-to-object-mapping
// a lot of libraries are listed in this question

// NOte from questions : Dozer is better option to map beans (if there are different names, structure,...)

public class App {
    public static void main(String[] args) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        //simplySettingProperties();

        copyDtoToEntityWithOneLine();
    }

    public static void simplySettingProperties() throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Course course = new Course();
        String name = "Computer science";
        List<String> codes = Arrays.asList("CS0", "CS1");

        //simple properties
        PropertyUtils.setSimpleProperty(course, "name", name);
        PropertyUtils.setSimpleProperty(course, "codes", codes);

        //indexed
        PropertyUtils.setIndexedProperty(course, "codes[0]", "123");

        //mapped
        Student student = new Student();
        student.setName("Ivan");
        PropertyUtils.setMappedProperty(course, "enrolledStudents(St-1)", student);


        System.out.println(course);
    }


    public static void copyDtoToEntityWithOneLine() throws InvocationTargetException, IllegalAccessException {
        Course course = new Course();
        String name = "Computer science";
        List<String> codes = Arrays.asList("CS0", "CS1");
        Map<String, Student> students = new HashMap<String, Student>();
        Student student = new Student();
        student.setName("Ivan");
        students.put(student.getName(), student);

        course.setName(name);
        course.setCodes(codes);
        course.setEnrolledStudents(students);

        CourseEntity courseEntity = new CourseEntity();
                BeanUtils.copyProperties(courseEntity, course);
        System.out.println(courseEntity);


        //Note: BeanUtils.populate - can be used to directly set values (some with different names or some nested)
    }
}
