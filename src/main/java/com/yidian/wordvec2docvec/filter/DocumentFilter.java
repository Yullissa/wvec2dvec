package com.yidian.wordvec2docvec.filter;



import com.yidian.wordvec2docvec.data.DocumentFeature;

import java.util.Optional;

public interface DocumentFilter {
    Optional<DocumentFeature> filter(String data);
}
