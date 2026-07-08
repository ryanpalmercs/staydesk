package com.staydesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.model.ContactInfo;
import com.staydesk.model.EncryptedString;
import com.staydesk.model.EncryptedToken;
import com.staydesk.security.PiiCipher;
import com.staydesk.security.TokenCipher;
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
    public JdbcCustomConversions jdbcCustomConversions(ObjectMapper objectMapper, TokenCipher tokenCipher, PiiCipher piiCipher) {
        return new JdbcCustomConversions(List.of(
                new Writer(objectMapper, piiCipher), new Reader(objectMapper, piiCipher),
                new EncryptedTokenWriter(tokenCipher), new EncryptedTokenReader(tokenCipher),
                new EncryptedStringWriter(piiCipher), new EncryptedStringReader(piiCipher)
        ));
    }


    @WritingConverter
    static class Writer implements Converter<ContactInfo, String> {
        private final ObjectMapper objectMapper;
        private final PiiCipher piiCipher;

        Writer(ObjectMapper objectMapper, PiiCipher piiCipher) {
            this.objectMapper = objectMapper;
            this.piiCipher = piiCipher;
        }

        @Override
        public String convert(ContactInfo contactInfo) {
            try {
                return piiCipher.encrypt(objectMapper.writeValueAsString(contactInfo));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @ReadingConverter
    static class Reader implements Converter<String, ContactInfo> {
        private final ObjectMapper objectMapper;
        private final PiiCipher piiCipher;

        Reader(ObjectMapper objectMapper, PiiCipher piiCipher) {
            this.objectMapper = objectMapper;
            this.piiCipher = piiCipher;
        }

        @Override
        public ContactInfo convert(String source) {
            try {
                return objectMapper.readValue(piiCipher.decrypt(source), ContactInfo.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @WritingConverter
    static class EncryptedStringWriter implements Converter<EncryptedString, String> {
        private final PiiCipher piiCipher;

        EncryptedStringWriter(PiiCipher piiCipher) {
            this.piiCipher = piiCipher;
        }

        @Override
        public String convert(EncryptedString source) {
            return piiCipher.encrypt(source.value());
        }
    }

    @ReadingConverter
    static class EncryptedStringReader implements Converter<String, EncryptedString> {
        private final PiiCipher piiCipher;

        EncryptedStringReader(PiiCipher piiCipher) {
            this.piiCipher = piiCipher;
        }

        @Override
        public EncryptedString convert(String source) {
            return new EncryptedString(piiCipher.decrypt(source));
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
