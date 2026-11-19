package com.agency.api.security;

import com.agency.api.domain.*;
import com.agency.api.repository.AccountRepository;

import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.CommandLineRunner; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration; import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AgentBootstrap {
    @Bean CommandLineRunner bootstrapAgent(AccountRepository accounts,PasswordEncoder encoder,@Value("${app.agent.email:}")String email,@Value("${app.agent.password:}")String password,@Value("${app.agent.name:Agente LarLink}")String name){return args->{if(email.isBlank()||password.isBlank())return;if(accounts.findByEmailIgnoreCase(email).isEmpty())accounts.save(new Account(name,email,encoder.encode(password),Role.AGENT));};}
}
