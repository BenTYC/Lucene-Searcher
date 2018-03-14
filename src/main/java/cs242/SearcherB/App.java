package cs242.SearcherB;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class App {
	// default setting
	static String indexPath = MyConstants.INDEX_DIRECTORY;
	static String indexSimilarity = MyConstants.SIMILARITY_TFIDF;
	static int hitsPerPage = MyConstants.MAX_SEARCH;

	public static void main(String[] args) throws IOException, ParseException {

		// parse settings
		for (int i = 0; i < args.length; i++) {
			switch (args[i].toLowerCase()) {
			case "-p":
				indexPath = args[i + 1];
				break;
			case "-s":
				indexSimilarity = args[i + 1];
				break;
			case "-h":
				hitsPerPage = Integer.parseInt(args[i + 1]);
				break;
			}
			i++;
		}

		// Add stop words
		// Analyzer analyzer = new StandardAnalyzer(addStopWords());
		Analyzer analyzer = new StandardAnalyzer();

		// To store an index on disk:
		Directory directory = FSDirectory.open(Paths.get(indexPath));

		// Searching
		// String searchText = "program";
		searchIndex(directory, analyzer, hitsPerPage);

		directory.close();
	}

	public static void searchIndex(Directory directory, Analyzer analyzer, int hitsPerPage)
			throws IOException, ParseException {
		// Date start = new Date();

		// Now search the index:
		DirectoryReader ireader = DirectoryReader.open(directory);
		IndexSearcher isearcher = new IndexSearcher(ireader);

		// Setup index searcher
		isearcher.setSimilarity(new BM25Similarity());
		isearcher.setSimilarity(new ClassicSimilarity());

		// Setup query parser
		String[] fields = { MyConstants.FIELD_TITLE, MyConstants.FIELD_CONTENT };
		HashMap<String, Float> boost = new HashMap<>();
		boost.put("title", 1.2f);
		boost.put("content", 1f);
		MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boost);

		// Parse a simple query that searches for "text":
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String queryString = null;

		while (true) {
			if (queryString == null) { // prompt the user
				System.out.println("\nEnter query or enter '-q' to leave: ");
			}

			String line = queryString != null ? queryString : in.readLine();

			if(line.equals("-q"))
				break;
			
			Query query = parser.parse(line);

			doPagingSearch(in, isearcher, query, hitsPerPage);
		}
		
		in.close();
		ireader.close();
	}

	public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, int hitsPerPage)
			throws IOException {
		TopDocs results = searcher.search(query, 10 * hitsPerPage);
		ScoreDoc[] hits = results.scoreDocs;

		int numTotalHits = (int)results.totalHits;
		System.out.println("\n"+ numTotalHits + " total matching Reddit pages\n");

		if (numTotalHits == 0) {
			return;
		}

		int start = 0;
		int end = Math.min(numTotalHits, hitsPerPage);
		while (true) {
			if (end > hits.length) {
				System.out.println("Only results 1 - " + hits.length + " of " + numTotalHits
						+ " total matching page collected.");
				System.out.println("Collect more (y/n) ?");
				String line = in.readLine();
				if (line.length() == 0 || line.charAt(0) == 'n') {
					break;
				}

				hits = searcher.search(query, numTotalHits).scoreDocs;
			}

			end = Math.min(hits.length, start + hitsPerPage);

			for (int i = start; i < end; i++) {
				Document doc = searcher.doc(hits[i].doc);
				System.out.println("title : " + doc.get(MyConstants.FIELD_TITLE) + "\nscore : " + hits[i].score);
				System.out.println("url   : " + doc.get(MyConstants.FIELD_URL) + "\n");
			}

			if (numTotalHits >= end) {
				boolean quit = false;
				while (true) {
					System.out.print("Press ");
					if (start - hitsPerPage >= 0) {
						System.out.print("(p)revious page, ");
					}
					if (start + hitsPerPage < numTotalHits) {
						System.out.print("(n)ext page, ");
					}
					System.out.println("\n(q)uit or enter number to jump to a page.");

					String line = in.readLine();
					if (line.length() == 0 || line.charAt(0) == 'q') {
						quit = true;
						break;
					}
					if (line.charAt(0) == 'p') {
						start = Math.max(0, start - hitsPerPage);
						break;
					} else if (line.charAt(0) == 'n') {
						if (start + hitsPerPage < numTotalHits) {
							start += hitsPerPage;
						}
						break;
					} else {
						int page = Integer.parseInt(line);
						if ((page - 1) * hitsPerPage < numTotalHits) {
							start = (page - 1) * hitsPerPage;
							break;
						} else {
							System.out.println("No such page");
						}
					}
				}

				if (quit) {
					break;
				}

				end = Math.min(numTotalHits, start + hitsPerPage);
			}
		}
	}
}
