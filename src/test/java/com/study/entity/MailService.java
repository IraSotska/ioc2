package com.study.entity;

import com.study.ioc.processor.PostConstruct;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailService implements IMailService {
    private String protocol;
    private int port;

    @PostConstruct
    private void init() {
        port = port * 2;
    }

    @Override
    public void sendEmail(User user, String message) {
        System.out.println("sending email with message: " + message);
    }
}
