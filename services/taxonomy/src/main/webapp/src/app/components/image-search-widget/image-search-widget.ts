import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CarouselModule } from 'primeng/carousel';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SearchService, SearchResult } from '../../services/search.service';
import { PartnerService } from '../../services/partner.service';

@Component({
  selector: 'app-image-search-widget',
  imports: [
    CommonModule,
    RouterLink,
    CarouselModule,
    ButtonModule,
    CardModule,
    ProgressSpinnerModule
  ],
  templateUrl: './image-search-widget.html',
  styleUrl: './image-search-widget.scss'
})
export class ImageSearchWidgetComponent implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  partnerId: string = '';
  partnerName: string = '';

  selectedFile: File | null = null;
  previewUrl: string | null = null;
  isDragging: boolean = false;

  results: SearchResult[] = [];
  queryText: string = '';
  loading: boolean = false;
  error: string = '';
  searchPerformed: boolean = false;

  responsiveOptions: any[] = [
    { breakpoint: '1400px', numVisible: 3, numScroll: 1 },
    { breakpoint: '1024px', numVisible: 2, numScroll: 1 },
    { breakpoint: '768px',  numVisible: 1, numScroll: 1 }
  ];

  constructor(
    private route: ActivatedRoute,
    private searchService: SearchService,
    private partnerService: PartnerService
  ) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.partnerId = params['partnerId'];
      if (this.partnerId) {
        this.partnerService.getPartnerById(this.partnerId).subscribe({
          next: (partner) => { this.partnerName = partner.name; },
          error: () => { this.partnerName = this.partnerId; }
        });
      }
    });
  }

  // ── File selection ──────────────────────────────────────────────────────────

  openFilePicker() {
    this.fileInput.nativeElement.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.setFile(input.files[0]);
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave() {
    this.isDragging = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.isDragging = false;
    const file = event.dataTransfer?.files[0];
    if (file && this.isImageFile(file)) {
      this.setFile(file);
    }
  }

  private setFile(file: File) {
    this.selectedFile = file;
    this.previewUrl = URL.createObjectURL(file);
    this.results = [];
    this.queryText = '';
    this.error = '';
    this.searchPerformed = false;
  }

  private isImageFile(file: File): boolean {
    return file.type.startsWith('image/');
  }

  clearFile() {
    this.selectedFile = null;
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
      this.previewUrl = null;
    }
    this.results = [];
    this.queryText = '';
    this.error = '';
    this.searchPerformed = false;
    this.fileInput.nativeElement.value = '';
  }

  // ── Search ──────────────────────────────────────────────────────────────────

  onSearch() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.error = '';
    this.results = [];
    this.searchPerformed = true;

    this.searchService.visualSearch(this.partnerId, this.selectedFile).subscribe({
      next: (response) => {
        this.queryText = response.queryText;
        this.results = response.results.map(r => ({ ...r, imageError: false }));
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to find similar products. Please try again.';
        this.loading = false;
        console.error('Visual search error:', err);
      }
    });
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  getImageUrl(filename: string): string {
    return `/api/images/${this.partnerId}/${filename}`;
  }

  handleImageError(result: any) {
    result.imageError = true;
  }
}

