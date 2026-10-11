import { useId, useState, type KeyboardEvent, type SubmitEvent } from 'react';
import { useNavigate } from 'react-router';
import { BlurredImage } from '../../components/BlurredImage.tsx';
import { es } from '../../i18n/es.ts';
import { searchText } from '../catalog/listingCriteria.ts';
import { isSearchable, useSuggestions } from './useSuggestions.ts';

/** The path of the results page of a text (UC-26). */
export function searchPath(text: string): string {
  return `/buscar?${new URLSearchParams({ q: text }).toString()}`;
}

interface SearchBoxProps {
  /** The text the field starts with: the one of the results page shown, if any. */
  initialText: string;
  autoFocus?: boolean;
  /** Called before leaving for a product or the results page, e.g. to close the full-screen search. */
  onNavigate?: () => void;
}

/**
 * The search field with its suggestions (UC-26), an ARIA combobox: arrows move through the suggestions, Enter opens
 * the highlighted one or else the results page, and Escape closes the list.
 */
export function SearchBox({ initialText, autoFocus = false, onNavigate }: SearchBoxProps) {
  const [text, setText] = useState(initialText);
  const [listOpen, setListOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const suggestions = useSuggestions(text);
  const navigate = useNavigate();
  const listId = useId();
  const expanded = listOpen && suggestions.length > 0 && isSearchable(searchText(text));
  const active = expanded ? suggestions[highlighted] : undefined;

  function go(path: string) {
    setListOpen(false);
    onNavigate?.();
    void navigate(path);
  }

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (active !== undefined) {
      go(`/producto/${active.slug}`);
      return;
    }
    const query = searchText(text);
    if (isSearchable(query)) {
      go(searchPath(query));
    }
  }

  function move(event: KeyboardEvent<HTMLInputElement>) {
    const count = suggestions.length;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (count === 0) {
        return;
      }
      event.preventDefault();
      setListOpen(true);
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setHighlighted((current) => (expanded ? (current + step + count) % count : step === 1 ? 0 : count - 1));
    } else if (event.key === 'Escape' && expanded) {
      // The first Escape only closes the list, not the full-screen search around it.
      event.preventDefault();
      setListOpen(false);
      setHighlighted(-1);
    }
  }

  return (
    <form role="search" className="search-box" onSubmit={submit}>
      <svg className="search-icon" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="11" cy="11" r="5.5" />
        <path d="M16 16l4 4" strokeLinecap="round" />
      </svg>
      <input
        type="search"
        role="combobox"
        aria-label={es.search.label}
        aria-autocomplete="list"
        aria-expanded={expanded}
        aria-controls={listId}
        aria-activedescendant={active === undefined ? undefined : `${listId}-${String(highlighted)}`}
        placeholder={es.search.placeholder}
        enterKeyHint="search"
        autoComplete="off"
        maxLength={400}
        // The full-screen search opens to type at once.
        autoFocus={autoFocus}
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          setListOpen(true);
          setHighlighted(-1);
        }}
        onKeyDown={move}
        onBlur={() => {
          setListOpen(false);
        }}
      />
      <ul id={listId} role="listbox" aria-label={es.search.suggestions} className="suggestions" hidden={!expanded}>
        {expanded &&
          suggestions.map((suggestion, index) => (
            <li
              key={suggestion.slug}
              id={`${listId}-${String(index)}`}
              role="option"
              aria-selected={index === highlighted}
              className="suggestion"
              // Keeps the focus in the field, so its blur does not close the list before the click lands.
              onMouseDown={(event) => {
                event.preventDefault();
              }}
              onClick={() => {
                go(`/producto/${suggestion.slug}`);
              }}
            >
              <span className="suggestion-ph">
                {suggestion.image !== null && (
                  <BlurredImage
                    src={suggestion.image.src}
                    srcSet={suggestion.image.srcSet}
                    sizes="48px"
                    alt=""
                    {...(suggestion.image.placeholder === undefined
                      ? {}
                      : { placeholder: suggestion.image.placeholder })}
                  />
                )}
              </span>
              <span>{suggestion.name}</span>
            </li>
          ))}
      </ul>
    </form>
  );
}
