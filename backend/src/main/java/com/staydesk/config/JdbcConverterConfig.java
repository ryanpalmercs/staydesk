package com.staydesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.model.ContactInfo;
import com.staydesk.model.EncryptedToken;
import com.staydesk.security.TokenCipher;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

import java.util.List;

@Configuration
public class JdbcConverterConfig {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions(ObjectMapper objectMapper, TokenCipher tokenCipher) {
        return new JdbcCustomConversions(List.of(
                new Writer(objectMapper), new Reader(objectMapper),
                new EncryptedTokenWriter(tokenCipher), new EncryptedTokenReader(tokenCipher)
        ));
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

    @WritingConverter
    static class EncryptedTokenWriter implements Converter<EncryptedToken, String> {
        private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedTokenWriter.class);
        private final TokenCipher tokenCipher;

        EncryptedTokenWriter(TokenCipher tokenCipher) {
            this.tokenCipher = tokenCipher;
        }

        @Override
        public String convert(EncryptedToken source) {
            LOGGER.info("Encrypting EncryptedToken for storage");
            return tokenCipher.encrypt(source.value());
        }
    }

    @ReadingConverter
    static class EncryptedTokenReader implements Converter<String, EncryptedToken> {
        private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedTokenReader.class);
        private final TokenCipher tokenCipher;

        EncryptedTokenReader(TokenCipher tokenCipher) {
            this.tokenCipher = tokenCipher;
        }

        @Override
        public EncryptedToken convert(String source) {
            LOGGER.info("Decrypting stored value into EncryptedToken");
            return new EncryptedToken(tokenCipher.decrypt(source));
        }
    }
}
