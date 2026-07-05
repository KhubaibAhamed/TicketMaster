package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.model.KnowledgeArticle;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import com.enterprise.ticketmaster.repository.KnowledgeArticleRepository;
import com.enterprise.ticketmaster.service.KnowledgeArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb/articles")
public class KnowledgeArticleController {

    private final KnowledgeArticleRepository articleRepository;
    private final CategoryRepository categoryRepository;
    private final KnowledgeArticleService articleService;

    public KnowledgeArticleController(KnowledgeArticleRepository articleRepository,
                                      CategoryRepository categoryRepository,
                                      KnowledgeArticleService articleService) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.articleService = articleService;
    }

    // Open to any authenticated role, including USER (restricted requesters) — that's the whole point of self-service
    @GetMapping
    public List<KnowledgeArticle> getAll() {
        return articleService.getAll();
    }

    @GetMapping("/search")
    public List<KnowledgeArticle> search(@RequestParam String q) {
        return articleService.searchRelated(q, 3);
    }

    public record ArticleRequest(String title, String body, Long categoryId) {}

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ArticleRequest request, Authentication authentication) {
        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Title cannot be blank.");
        }
        if (request.body() == null || request.body().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Body cannot be blank.");
        }

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(request.title().trim());
        article.setBody(request.body());
        article.setCategory(resolveCategory(request.categoryId()));
        article.setCreatedBy(authentication != null ? authentication.getName() : "unknown");

        return ResponseEntity.status(HttpStatus.CREATED).body(articleRepository.save(article));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ArticleRequest request) {
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found with id: " + id));

        if (request.title() != null && !request.title().isBlank()) article.setTitle(request.title().trim());
        if (request.body() != null && !request.body().isBlank()) article.setBody(request.body());
        if (request.categoryId() != null) article.setCategory(resolveCategory(request.categoryId()));

        return ResponseEntity.ok(articleRepository.save(article));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!articleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Article not found with id: " + id);
        }
        articleRepository.deleteById(id);
        return ResponseEntity.ok("Article deleted successfully.");
    }

    // Restricted to non-USER roles: requesters shouldn't be turning their own tickets into KB articles
    @PostMapping("/from-ticket/{ticketId}")
    public ResponseEntity<?> createFromTicket(@PathVariable Long ticketId, Authentication authentication) {
        boolean isRequester = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        if (isRequester) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only agents or admins can create KB articles from tickets.");
        }
        String username = authentication != null ? authentication.getName() : "unknown";
        KnowledgeArticle created = articleService.createFromTicket(ticketId, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
    }
}