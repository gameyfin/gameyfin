import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class CookieService {

  constructor() {
  }

  setCookie(name: string, value: any): void {
    document.cookie = `${name}=${value.toString()};`;
  }

  getCookie(name: string): string | null {
    let end;
    const dc = document.cookie;
    const prefix = name + "=";
    let begin = dc.indexOf("; " + prefix);

    if (begin == -1) {
      begin = dc.indexOf(prefix);
      if (begin != 0) return null;
    } else {
      begin += 2;
      end = document.cookie.indexOf(";", begin);
      if (end == -1) {
        end = dc.length;
      }
    }

    return decodeURI(dc.substring(begin + prefix.length, end));
  }
}
