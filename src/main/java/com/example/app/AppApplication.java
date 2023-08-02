package com.example.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.stream.Stream;

@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }

    @Bean
    InitializingBean applicationRunner(CustomerRepository repository) {
        return () -> Stream.of("jlong", "mbhave", "dsyer", "jhoeller", "rjohnson").forEach(name -> repository.save(new Customer(null, name)));
    }

}

@EnableWebSecurity
class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeRequests(a -> a.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withDefaultPasswordEncoder().username("jlong").password("pw").roles("USER").build());
    }
}


@Controller
@ResponseBody
class CustomerHttpController {

    private final CustomerRepository repository;

    CustomerHttpController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/customers")
    Collection<Customer> customers() {
        return this.repository.findAll();
    }
}

interface CustomerRepository extends JpaRepository<Customer, Integer> {
}

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
class Customer {

    @Id
    @GeneratedValue
    private Integer id;

    private String name;
}

@Configuration
@EnableBatchProcessing
class BatchJobConfiguration {

    @Bean
    JdbcCursorItemReader<Customer> customerJdbcCursorItemReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<Customer>()
                .name("jdbc")
                .dataSource(dataSource)
                .sql("select * from customer")
                .rowMapper((rs, rowNum) -> new Customer(rs.getInt("id"), rs.getString("name")))
                .build();
    }

    @Bean
    Job job(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory,
            JdbcCursorItemReader<Customer> customerJdbcCursorItemReader) {
        return jobBuilderFactory
                .get("job")
                .start(stepBuilderFactory.get("one")
                        .<Customer, Customer>chunk(100)
                        .reader(customerJdbcCursorItemReader)
                        .writer(items -> items.forEach(item -> System.out.println("batch: " + item.toString())))
                        .build())
                .build();
    }
}