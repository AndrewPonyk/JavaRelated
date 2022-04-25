package com.ap.interview1602preparingbootdatah2.web;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class BooksControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @Test
    void addBook() {
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    scripts = "/test-data.sql")
    void deleteBook() throws Exception {
        //verify there are 2 items in db
        mockMvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.*", Matchers.hasSize(2)))
                .andDo(MockMvcResultHandlers.print());

        mockMvc.perform(MockMvcRequestBuilders.get("/delete/1"))
        .andExpect(MockMvcResultMatchers.status().isOk());

        //verify there is 1 item in db
        mockMvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.*", Matchers.hasSize(1)))
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void getBooks() {
    }

    @Test
    void getBooksByName() {
    }

    @Test
    void getBooksByNameAndAuthor() {
    }
}
//EXAMPLEEEEEEEEEEEEEEEEEEEEES
/**
 mockMvc.perform(get("/myapp/ressource/1"))
 .andExpect(status().isOk())
 .andExpect(jsonPath("$[0].date").exists())
 .andExpect(jsonPath("$[0].type").value("1"))
 .andExpect(jsonPath("$[0].element.list").value(new ArrayList<>()))
 .andExpect(jsonPath("$[0].element.id").value("42"))
 .andExpect(jsonPath("$[0].element.*", hasSize(2)))
 .andExpect(jsonPath("$[0].*", hasSize(3)))
 .andExpect(jsonPath("$.*", hasSize(1)));
 */