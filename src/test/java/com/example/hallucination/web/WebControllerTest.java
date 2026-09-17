package com.example.hallucination.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "detector.mode=mock")
@AutoConfigureMockMvc
class WebControllerTest {
    @Autowired MockMvc mvc;
    @Test
    void pageIsAvailable() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void detectsUploadedReplyArray() throws Exception {
        mvc.perform(post("/api/detect").contentType("application/json").content("""
                [{"id":"new","user_question":"退货？","system_reply":"支持30天无理由退货", "knowledge_base":"支持7天无理由退货"}]
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].is_hallucination").value(true));
    }

    @Test
    void invalidUploadIsRejected() throws Exception {
        mvc.perform(post("/api/detect").contentType("application/json").content("{bad json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/detect").contentType("application/json").content("[]"))
                .andExpect(status().isBadRequest());
    }
}
