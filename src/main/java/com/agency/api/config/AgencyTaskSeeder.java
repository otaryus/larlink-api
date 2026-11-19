package com.agency.api.config;

import com.agency.api.domain.AgencyTask;
import com.agency.api.repository.AgencyTaskRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.List;

@Component @Profile("dev")
public class AgencyTaskSeeder implements CommandLineRunner {
    private final AgencyTaskRepository tasks;
    public AgencyTaskSeeder(AgencyTaskRepository tasks){this.tasks=tasks;}
    @Override public void run(String... args){
        List<AgencyTask> defaults=List.of(new AgencyTask("Limpeza geral","Limpeza de divisões e superfícies.","CLEANING"),new AgencyTask("Lavagem de roupa","Lavagem e secagem de roupa.","LAUNDRY"),new AgencyTask("Engomadoria","Passagem e organização de roupa.","LAUNDRY"),new AgencyTask("Preparação de refeições","Preparação de refeições domésticas.","COOKING"),new AgencyTask("Cuidado de crianças","Acompanhamento e cuidado de crianças.","CHILDCARE"),new AgencyTask("Cuidado de idosos","Apoio e acompanhamento de idosos.","ELDERLY_CARE"),new AgencyTask("Jardinagem","Cuidados básicos de jardim.","GARDENING"),new AgencyTask("Organização doméstica","Organização de quartos e áreas comuns.","CLEANING"),new AgencyTask("Outras actividades","Tarefa descrita pelo cliente no escopo.","OTHER"));
        defaults.forEach(task->{if(!tasks.existsByNameIgnoreCase(task.getName()))tasks.save(task);});
    }
}
