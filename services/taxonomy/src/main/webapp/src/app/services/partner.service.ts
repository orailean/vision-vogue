import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Partner {
  id: string;
  name: string;
}

@Injectable({
  providedIn: 'root'
})
export class PartnerService {
  private baseUrl = '/api/partners';

  constructor(private http: HttpClient) { }

  getPartnerById(partnerId: string): Observable<Partner> {
    return this.http.get<Partner>(`${this.baseUrl}/${partnerId}`);
  }
}

