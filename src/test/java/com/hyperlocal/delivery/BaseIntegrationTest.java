package com.hyperlocal.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hyperlocal.delivery.model.Business;
import com.hyperlocal.delivery.model.User;
import com.hyperlocal.delivery.repository.BusinessRepository;
import com.hyperlocal.delivery.repository.UserRepository;
import com.hyperlocal.delivery.security.AuthenticatedPrincipal;
import com.hyperlocal.delivery.security.JwtUtil;

import jakarta.persistence.EntityManager;

/**
 * Base class for integration tests. Activates the "test" profile (H2 in-memory
 * database) and wraps each test in a transaction that rolls back automatically.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected BusinessRepository businessRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EntityManager entityManager;

    /**
     * Create a test business and persist it.
     */
    protected Business createAndSaveBusiness(String name, String email) {
        Business business = TestDataFactory.createBusiness(name, email);
        return businessRepository.save(business);
    }

    /**
     * Create a test owner user and persist it.
     */
    protected User createAndSaveOwner(Business business, String email) {
        User owner = TestDataFactory.createOwner(business, email);
        return userRepository.save(owner);
    }

    /**
     * Create a test agent user and persist it.
     */
    protected User createAndSaveAgent(Business business, String email) {
        User agent = TestDataFactory.createAgent(business, email);
        return userRepository.save(agent);
    }

    /**
     * Generate a valid JWT access token for the given user.
     */
    protected String tokenFor(User user) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                user.getId(), user.getEmail(), user.getBusiness().getId(), user.getRole());
        return jwtUtil.signAccess(principal);
    }

    /**
     * Flush pending changes and clear the persistence context cache.
     * Useful before GET requests that need to see freshly inserted data
     * via entity graphs.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
