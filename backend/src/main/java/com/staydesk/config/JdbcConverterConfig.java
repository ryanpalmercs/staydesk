package com.staydesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.model.ContactInfo;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

import java.util.List;

@Configuration
public class JdbcConverterConfig  {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions(ObjectMapper objectMapper) {
        return new JdbcCustomConversions(List.of(new Writer(objectMapper), new Reader(objectMapper)));
    }

    @WritingConverter
    static class Writer implements Converter<ContactInfo, PGobject> {
        private final ObjectMapper objectMapper;

        Writer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public PGobject convert(ContactInfo contactInfo) {
            try {
                PGobject object = new PGobject();
                object.setType("jsonb");
                object.setValue(objectMapper.writeValueAsString(contactInfo));
                return object;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @ReadingConverter
    static class Reader implements Converter<PGobject, ContactInfo> {
        private final ObjectMapper objectMapper;

        Reader(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public ContactInfo convert(PGobject source) {
            try {
                return objectMapper.readValue(source.getValue(), ContactInfo.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
