/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockitousage.sample;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.RequiresValidState;

public class MockitoSampleTest extends RequiresValidState {
    
    private ArticleCalculator mockCalculator;
    private ArticleDatabase mockDatabase;
    
    private ArticleManager articleManager;
    
    @Before
    public void setup() {
        mockCalculator = mock(ArticleCalculator.class);
        mockDatabase = mock(ArticleDatabase.class);
        
        articleManager = new ArticleManager(mockCalculator, mockDatabase);
    }

    @Test
    public void managerCountsArticlesAndSavesThemInTheDatabase() {
        stub(mockCalculator.countArticles("Guardian")).toReturn(12);
        stub(mockCalculator.countArticlesInPolish("Guardian")).toReturn(5);
        
        articleManager.updateArticleCounters("Guardian");
        
        verify(mockDatabase).updateNumberOfArticles("Guardian", 12);
        verify(mockDatabase).updateNumberOfPolishArticles("Guardian", 5);
        verify(mockDatabase).updateNumberOfEnglishArticles("Guardian", 7);
        
        verifyNoMoreInteractions(mockDatabase);
    }
    
    @Test
    public void managerCountsArticlesUsingCalculator() {
        articleManager.updateArticleCounters("Guardian");

        verify(mockCalculator).countArticles("Guardian");
        verify(mockCalculator).countArticlesInPolish("Guardian");
        
        verifyNoMoreInteractions(mockCalculator);
    }
    
    @Test
    public void managerSavesArticlesInTheDatabase() {
        articleManager.updateArticleCounters("Guardian");

        verify(mockDatabase).updateNumberOfArticles("Guardian", 0);
        verify(mockDatabase).updateNumberOfPolishArticles("Guardian", 0);
        verify(mockDatabase).updateNumberOfEnglishArticles("Guardian", 0);
        
        verifyNoMoreInteractions(mockDatabase);
    }
    
    @Test
    public void managerUpdatesNumberOfRelatedArticles() {
        Article articleOne = new Article();
        Article articleTwo = new Article();
        Article articleThree = new Article();
        
        stub(mockCalculator.countNumberOfRelatedArticles(articleOne)).toReturn(1);
        stub(mockCalculator.countNumberOfRelatedArticles(articleTwo)).toReturn(12);
        stub(mockCalculator.countNumberOfRelatedArticles(articleThree)).toReturn(0);
        
        stub(mockDatabase.getArticlesFor("Guardian")).toReturn(Arrays.asList(articleOne, articleTwo, articleThree)); 
        
        articleManager.updateRelatedArticlesCounters("Guardian");

        verify(mockDatabase).save(articleOne);
        verify(mockDatabase).save(articleTwo);
        verify(mockDatabase).save(articleThree);
    }
    
    @Test
    public void shouldPersistRecalculatedArticle() {
        Article articleOne = new Article();
        Article articleTwo = new Article();
        
        stub(mockCalculator.countNumberOfRelatedArticles(articleOne)).toReturn(1);
        stub(mockCalculator.countNumberOfRelatedArticles(articleTwo)).toReturn(12);
        
        stub(mockDatabase.getArticlesFor("Guardian")).toReturn(Arrays.asList(articleOne, articleTwo)); 
        
        articleManager.updateRelatedArticlesCounters("Guardian");

        InOrder inOrder = inOrder(mockDatabase, mockCalculator);
        
        inOrder.verify(mockCalculator).countNumberOfRelatedArticles((Article) anyObject());
        inOrder.verify(mockDatabase, atLeastOnce()).save((Article) anyObject());
    }
}