import { Directive, Input, OnChanges, SimpleChanges, ElementRef } from '@angular/core';

@Directive({
  selector: '[progressBarColor]'
})
export class ProgressBarColorDirective implements OnChanges{
  static counter = 0;

  @Input() progressBarColor!: string;
  styleEl:HTMLStyleElement = document.createElement('style');

  //generate unique attribule which we will use to minimise the scope of our dynamic style
  uniqueAttr = `app-progress-bar-color-${ProgressBarColorDirective.counter++}`;

  constructor(private el: ElementRef) {
    const nativeEl: HTMLElement = this.el.nativeElement;
    nativeEl.setAttribute(this.uniqueAttr,'');
    nativeEl.appendChild(this.styleEl);
  }

  ngOnChanges(changes: SimpleChanges): void{
    this.updateColor();
  }

  updateColor(): void{
    // update dynamic style with the uniqueAttr
    this.styleEl.innerText = `
      [${this.uniqueAttr}] .mat-progress-bar-fill::after {
        background-color: ${this.progressBarColor};
      }
    `;
  }

}
