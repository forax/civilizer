package com.github.forax.civilizer;

import java.io.IOException;

public class AllRewriter {
  public static void main(String[] args) throws IOException {
    System.out.println("--- value ---");
    Rewriter.main(args);
    System.out.println("--- parametric ---");
    VMRewriter.main(args);
  }
}
