import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CarouselModule } from 'primeng/carousel';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SearchService, SearchResult } from '../../services/search.service';
import { PartnerService } from '../../services/partner.service';

@Component({
  selector: 'app-search-widget',
  imports: [
    CommonModule,
    FormsModule,
    CarouselModule,
    InputTextModule,
    ButtonModule,
    CardModule,
    ProgressSpinnerModule
  ],
  templateUrl: './search-widget.html',
  styleUrl: './search-widget.scss'
})
export class SearchWidgetComponent implements OnInit {
  partnerId: string = '';
  partnerName: string = '';
  searchQuery: string = '';
  results: SearchResult[] = [];
  loading: boolean = false;
  error: string = '';
  searchPerformed: boolean = false;
  responsiveOptions: any[] = [
    {
      breakpoint: '1400px',
      numVisible: 3,
      numScroll: 1
    },
    {
      breakpoint: '1024px',
      numVisible: 2,
      numScroll: 1
    },
    {
      breakpoint: '768px',
      numVisible: 1,
      numScroll: 1
    }
  ];

  constructor(
    private route: ActivatedRoute,
    private searchService: SearchService,
    private partnerService: PartnerService
  ) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.partnerId = params['partnerId'];

      // Fetch partner name
      if (this.partnerId) {
        this.partnerService.getPartnerById(this.partnerId).subscribe({
          next: (partner) => {
            this.partnerName = partner.name;
          },
          error: (err) => {
            console.error('Failed to fetch partner details:', err);
            this.partnerName = this.partnerId; // Fallback to ID if fetch fails
          }
        });
      }
    });
  }

  onSearch() {
    if (!this.searchQuery.trim()) {
      return;
    }

    this.loading = true;
    this.error = '';
    this.results = [];
    this.searchPerformed = true;

    this.searchService.semanticSearch(this.partnerId, this.searchQuery, 10, 0.8)
      .subscribe({
        next: (results) => {
          // Add imageError property to each result
          this.results = results.map(r => ({ ...r, imageError: false }));
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Failed to fetch search results. Please try again.';
          this.loading = false;
          console.error('Search error:', err);
        }
      });
  }

  getImageUrl(filename: string): string {
    return `/api/images/${this.partnerId}/${filename}`;
  }

  handleImageError(result: any) {
    console.warn('Failed to load image:', result.filename);
    result.imageError = true;
  }

  onKeyPress(event: KeyboardEvent) {
    if (event.key === 'Enter') {
      this.onSearch();
    }
  }
}
