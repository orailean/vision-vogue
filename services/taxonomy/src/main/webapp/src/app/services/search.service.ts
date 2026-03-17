import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SearchResult {
  recordId: string;
  filename: string;
  topCategoryLabel: string;
  topCategoryConfidence: number;
  similarity: number;
  combinedScore: number;
  text: string;
}

export interface VisualSearchResponse {
  queryText: string;
  results: SearchResult[];
}

@Injectable({
  providedIn: 'root'
})
export class SearchService {
  private baseUrl = '/api/search';

  constructor(private http: HttpClient) { }

  semanticSearch(partnerId: string, query: string, topK: number = 10, simWeight: number = 0.8): Observable<SearchResult[]> {
    const params = new HttpParams()
      .set('partnerId', partnerId)
      .set('q', query)
      .set('topK', topK.toString())
      .set('simWeight', simWeight.toString());

    return this.http.get<SearchResult[]>(`${this.baseUrl}/semantic`, { params });
  }

  visualSearch(partnerId: string, image: File, topK: number = 8, simWeight: number = 0.85): Observable<VisualSearchResponse> {
    const formData = new FormData();
    formData.append('image', image);

    const params = new HttpParams()
      .set('partnerId', partnerId)
      .set('topK', topK.toString())
      .set('simWeight', simWeight.toString());

    return this.http.post<VisualSearchResponse>(`${this.baseUrl}/visual`, formData, { params });
  }
}

