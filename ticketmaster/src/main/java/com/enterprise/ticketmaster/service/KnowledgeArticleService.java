package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Comment;
import com.enterprise.ticketmaster.model.KnowledgeArticle;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.KnowledgeArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeArticleService {

    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "was", "with", "this", "that", "have", "has",
            "not", "can", "but", "you", "your", "our", "when", "how", "what", "why",
            "issue", "problem", "please", "help", "ticket"
    );

    private final KnowledgeArticleRepository articleRepository;
    private final TicketService ticketService;

    public KnowledgeArticleService(KnowledgeArticleRepository articleRepository, TicketService ticketService) {
        this.articleRepository = articleRepository;
        this.ticketService = ticketService;
    }

    public List<KnowledgeArticle> getAll() {
        return articleRepository.findAllByOrderByTitleAsc();
    }

    /**
     * Very deliberately simple keyword-overlap scoring, matching this codebase's
     * existing ClassifierService approach rather than pulling in a search engine —
     * title matches count 3x, body matches count 1x. Returns only articles with
     * a positive score, best first.
     */
    public List<KnowledgeArticle> searchRelated(String queryText, int limit) {
        Set<String> queryTokens = tokenize(queryText);
        if (queryTokens.isEmpty()) return List.of();

        List<KnowledgeArticle> all = articleRepository.findAll();
        Map<KnowledgeArticle, Integer> scored = new HashMap<>();

        for (KnowledgeArticle article : all) {
            Set<String> titleTokens = tokenize(article.getTitle());
            Set<String> bodyTokens = tokenize(article.getBody());

            int score = 0;
            for (String token : queryTokens) {
                if (titleTokens.contains(token)) score += 3;
                if (bodyTokens.contains(token)) score += 1;
            }
            if (score > 0) scored.put(article, score);
        }

        return scored.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(WORD_SPLIT.split(text.toLowerCase()))
                .filter(w -> w.length() >= 3 && !STOPWORDS.contains(w))
                .collect(Collectors.toSet());
    }

    @Transactional
    public KnowledgeArticle createFromTicket(Long ticketId, String username) {
        Ticket ticket = ticketService.getTicketById(ticketId);

        StringBuilder body = new StringBuilder();
        body.append("## Problem\n").append(ticket.getDescription()).append("\n\n");

        String resolutionNotes = ticket.getComments().stream()
                .filter(c -> !c.isInternal())
                .sorted(Comparator.comparing(Comment::getCreatedAt))
                .map(c -> "- " + c.getContent())
                .collect(Collectors.joining("\n"));

        if (!resolutionNotes.isBlank()) {
            body.append("## Resolution\n").append(resolutionNotes);
        }

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(ticket.getTitle());
        article.setBody(body.toString());
        article.setCategory(ticket.getCategory());
        article.setSourceTicketId(ticket.getId());
        article.setCreatedBy(username);

        return articleRepository.save(article);
    }
}