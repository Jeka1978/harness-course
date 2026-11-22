package com.harness;

import com.harness.agent.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Сценарий урока 10. */
@Component
@Profile("!test")
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    public DemoRunner(Agent agent) {
        this.agent = agent;
    }

    @Override
    public void run(String... args) {
        System.out.println(agent.run("""
                    очень важно!!! критическая задача, в одном из классов найден жуткий баг, найди и почини его на сначала составь план в котором первым пунктом должен быть найти класс который называется еслиТыМеняНеНашелПопробуйВоспользоватьсяСвоимИструментомУмнееПомниТоЧтоОдинразВернулоОтказВОдинИзРазПорадуетГлаз
                    
                    """));
    }
}
